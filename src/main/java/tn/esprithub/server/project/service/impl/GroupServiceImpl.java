package tn.esprithub.server.project.service.impl;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprithub.server.academic.entity.Classe;
import tn.esprithub.server.academic.repository.ClasseRepository;
import tn.esprithub.server.project.entity.Group;
import tn.esprithub.server.project.repository.GroupRepository;
import tn.esprithub.server.project.service.GroupService;
import tn.esprithub.server.project.repository.ProjectRepository;
import tn.esprithub.server.user.repository.UserRepository;
import tn.esprithub.server.project.entity.Project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import tn.esprithub.server.project.dto.GroupCreateDto;
import tn.esprithub.server.project.dto.GroupUpdateDto;
import tn.esprithub.server.integration.github.GithubService;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.repository.service.RepositoryEntityService;
import tn.esprithub.server.repository.service.RepositoryService;

@Service
public class GroupServiceImpl implements GroupService {
    private static final Logger logger = LoggerFactory.getLogger(GroupServiceImpl.class);
    private final GroupRepository groupRepository;
    private final ClasseRepository classeRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final GithubService githubService;
    private final RepositoryEntityService repositoryEntityService;
    private final RepositoryService repositoryService;

    public GroupServiceImpl(GroupRepository groupRepository, ClasseRepository classeRepository, ProjectRepository projectRepository, UserRepository userRepository, GithubService githubService, RepositoryEntityService repositoryEntityService, RepositoryService repositoryService) {
        this.groupRepository = groupRepository;
        this.classeRepository = classeRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.githubService = githubService;
        this.repositoryEntityService = repositoryEntityService;
        this.repositoryService = repositoryService;
    }

    @Override
    public Group createGroup(Group group) {
        logger.info("Incoming group payload: {}", group);
        if (group.getClasse() == null || group.getClasse().getId() == null) {
            throw new IllegalArgumentException("Missing or invalid 'classe' (class) in group payload");
        }
        if (group.getProject() == null || group.getProject().getId() == null) {
            throw new IllegalArgumentException("Missing or invalid 'project' in group payload");
        }
        if (group.getStudents() == null || group.getStudents().isEmpty()) {
            throw new IllegalArgumentException("Group must have at least one student");
        }
        // Fetch and set managed Classe
        Classe managedClasse = classeRepository.findById(group.getClasse().getId()).orElseThrow(() -> new IllegalArgumentException("Classe not found with provided id"));
        group.setClasse(managedClasse);
        // Fetch and set managed Project
        Project managedProject = projectRepository.findById(group.getProject().getId()).orElseThrow(() -> new IllegalArgumentException("Project not found with provided id"));
        group.setProject(managedProject);
        // Fetch and set managed Students
        group.setStudents(group.getStudents().stream()
            .map(s -> userRepository.findById(s.getId()).orElseThrow(() -> new IllegalArgumentException("Student not found with id: " + s.getId())))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
        return groupRepository.save(group);
    }

    @Override
    @Transactional
    public Group createGroup(GroupCreateDto dto, Authentication authentication) {
        logger.info("Incoming group create DTO: {}", dto);
        if (dto.getClasseId() == null) {
            throw new IllegalArgumentException("Missing or invalid 'classeId' in group payload");
        }
        if (dto.getProjectId() == null) {
            throw new IllegalArgumentException("Missing or invalid 'projectId' in group payload");
        }
        if (dto.getStudentIds() == null || dto.getStudentIds().isEmpty()) {
            throw new IllegalArgumentException("Group must have at least one student");
        }
        
        // Get the authenticated teacher
        String teacherEmail = getUserEmailFromAuthentication(authentication);
        User teacher = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        
        // Validate teacher has GitHub credentials
        if (teacher.getGithubToken() == null || teacher.getGithubToken().isBlank()) {
            throw new IllegalArgumentException("Teacher must have GitHub token configured to create groups");
        }
        if (teacher.getGithubUsername() == null || teacher.getGithubUsername().isBlank()) {
            throw new IllegalArgumentException("Teacher must have GitHub username configured to create groups");
        }
        
        Classe managedClasse = classeRepository.findById(dto.getClasseId()).orElseThrow(() -> new IllegalArgumentException("Classe not found with provided id"));
        Project managedProject = projectRepository.findById(dto.getProjectId()).orElseThrow(() -> new IllegalArgumentException("Project not found with provided id"));
        var managedStudents = dto.getStudentIds().stream()
            .map(id -> userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Student not found with id: " + id)))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        Group group = new Group();
        group.setName(dto.getName());
        group.setClasse(managedClasse);
        group.setProject(managedProject);
        group.setStudents(managedStudents);
        Group savedGroup = groupRepository.save(group);
        
        // --- GITHUB INTEGRATION ---
        boolean repoCreated = false;
        String repoUrl = null;
        String repoError = null;
        tn.esprithub.server.repository.entity.Repository repository = null;
        
        try {
            // Compose repo name: projectName-className-groupName
            String baseRepoName = managedProject.getName() + "-" + managedClasse.getNom() + "-" + group.getName();
            
            // Clean the repository name to be GitHub-compatible
            String cleanRepoName = baseRepoName
                    .replaceAll("[^a-zA-Z0-9._-]", "-") // Replace invalid characters with hyphens
                    .replaceAll("-+", "-") // Replace multiple consecutive hyphens with single hyphen
                    .replaceAll("^-|-$", "") // Remove leading/trailing hyphens
                    .toLowerCase(); // Convert to lowercase
            
            // Add a unique suffix based on the group ID to ensure uniqueness
            String groupIdSuffix = savedGroup.getId().toString().substring(0, 8);
            String repoName = cleanRepoName + "-" + groupIdSuffix;
            
            // Ensure the repository name doesn't exceed GitHub's 100 character limit
            if (repoName.length() > 100) {
                repoName = repoName.substring(0, 90) + "-" + groupIdSuffix;
            }
            
            String teacherToken = teacher.getGithubToken();
            
            boolean isPrivate = dto.getIsPrivate() != null ? dto.getIsPrivate() : true;
            String gitignoreTemplate = dto.getGitignoreTemplate();
            logger.info("Creating GitHub repository with name: {} (original: {}), private: {}, gitignore: {}", repoName, baseRepoName, isPrivate, gitignoreTemplate);
            String repoFullName = githubService.createRepositoryForUser(repoName, teacherToken, isPrivate, gitignoreTemplate);
            
            if (repoFullName != null && !repoFullName.isBlank()) {
                logger.info("GitHub repository created successfully: {}", repoFullName);
                repoCreated = true;
                repoUrl = "https://github.com/" + repoFullName;
                
                // Create repository entity in database FIRST
                repository = tn.esprithub.server.repository.entity.Repository.builder()
                        .name(repoName)
                        .fullName(repoFullName)
                        .description("Repository for group project: " + repoName)
                        .url(repoUrl)
                        .isPrivate(isPrivate)
                        .defaultBranch("main")
                        .cloneUrl("https://github.com/" + repoFullName + ".git")
                        .sshUrl("git@github.com:" + repoFullName + ".git")
                        .owner(teacher)
                        .isActive(true)
                        .build();
                
                logger.info("Saving repository entity to database...");
                repository = repositoryEntityService.createRepository(repository);
                logger.info("Repository entity saved with ID: {}", repository.getId());
                
                // Associate repository with group IMMEDIATELY
                logger.info("Associating repository {} with group {}", repository.getId(), savedGroup.getId());
                savedGroup.setRepository(repository);
                savedGroup = groupRepository.save(savedGroup);
                logger.info("Group updated with repository_id: {}", 
                    savedGroup.getRepository() != null ? savedGroup.getRepository().getId() : "null");
                
                // Force a flush to ensure database is updated
                groupRepository.flush();
                logger.info("Database flushed - repository association should be persisted");
                
            } else {
                logger.warn("GitHub repository creation returned empty repo name for group: {}", savedGroup.getName());
                repoError = "GitHub repository creation returned empty repo name.";
            }
        } catch (Exception e) {
            logger.error("GitHub repository creation and linking failed for group {}: {}", savedGroup.getName(), e.getMessage(), e);
            repoError = e.getMessage();
        }
        
        // Only after repository is linked, try student invitations (in separate try-catch)
        if (repoCreated && repository != null) {
            try {
                String repoFullName = repository.getFullName();
                String teacherToken = teacher.getGithubToken();
                logger.info("Starting student invitations and branch creation for repository: {}", repoFullName);
                
                for (var student : managedStudents) {
                    if (student.getGithubUsername() != null && !student.getGithubUsername().isBlank()) {
                        try {
                            logger.info("Inviting student {} to repository {}", student.getGithubUsername(), repoFullName);
                            githubService.inviteUserToRepo(repoFullName, student.getGithubUsername(), teacherToken);
                            
                            String branchName = student.getFirstName() + student.getLastName();
                            logger.info("Creating branch {} for student {}", branchName, student.getGithubUsername());
                            githubService.createBranch(repoFullName, branchName, teacherToken);
                        } catch (Exception studentException) {
                            logger.warn("Failed to invite student {} or create branch: {}", 
                                student.getGithubUsername(), studentException.getMessage());
                            // Continue with other students even if one fails
                        }
                    } else {
                        logger.info("Student {} has no GitHub username, skipping invitation", student.getEmail());
                    }
                }
                logger.info("Completed student invitations and branch creation for repository: {}", repoFullName);
            } catch (Exception inviteException) {
                logger.warn("Error during student invitations/branch creation (repository already linked): {}", inviteException.getMessage());
                // Student invitation failures don't affect the repository creation success
            }
        }
        
        // Attach repo info to group for controller (not persisted)
        savedGroup.setRepoCreated(repoCreated);
        savedGroup.setRepoUrl(repoUrl);
        savedGroup.setRepoError(repoError);
        return savedGroup;
    }

    private String getUserEmailFromAuthentication(Authentication authentication) {
        if (authentication.getPrincipal() instanceof UserDetails userDetails) {
            return userDetails.getUsername(); // In our case, username IS the email
        } else {
            return authentication.getName(); // Fallback
        }
    }

    @Override
    public Group updateGroup(UUID id, GroupUpdateDto dto) {
        if (dto.getClasseId() == null) {
            throw new IllegalArgumentException("Missing or invalid 'classeId' in group payload");
        }
        if (dto.getProjectId() == null) {
            throw new IllegalArgumentException("Missing or invalid 'projectId' in group payload");
        }
        if (dto.getStudentIds() == null || dto.getStudentIds().isEmpty()) {
            throw new IllegalArgumentException("Group must have at least one student");
        }

        // Get existing group to compare students
        Group group = groupRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Group not found with provided id"));

        // Get current student IDs for comparison
        Set<UUID> currentStudentIds = group.getStudents().stream()
            .map(User::getId)
            .collect(Collectors.toSet());

        // Get new student IDs
        Set<UUID> newStudentIds = new HashSet<>(dto.getStudentIds());

        // Find newly added students (students in new list but not in current list)
        Set<UUID> addedStudentIds = newStudentIds.stream()
            .filter(studentId -> !currentStudentIds.contains(studentId))
            .collect(Collectors.toSet());

        Classe managedClasse = classeRepository.findById(dto.getClasseId()).orElseThrow(() -> new IllegalArgumentException("Classe not found with provided id"));
        Project managedProject = projectRepository.findById(dto.getProjectId()).orElseThrow(() -> new IllegalArgumentException("Project not found with provided id"));
        var managedStudentIds = dto.getStudentIds();
        var managedStudents = managedStudentIds.stream()
            .map(studentId -> userRepository.findById(studentId).orElseThrow(() -> new IllegalArgumentException("Student not found with id: " + studentId)))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        // Update group properties
        group.setName(dto.getName());
        group.setClasse(managedClasse);
        group.setProject(managedProject);
        group.setStudents(new java.util.ArrayList<>(managedStudents));

        // Save the updated group
        Group savedGroup = groupRepository.save(group);

        // Add new students as repository collaborators if group has an associated repository
        if (!addedStudentIds.isEmpty() && group.getRepository() != null) {
            addNewStudentsAsRepositoryCollaborators(group, addedStudentIds, managedProject);
        }

        return savedGroup;
    }

    /**
     * Helper method to add new students as repository collaborators
     */
    private void addNewStudentsAsRepositoryCollaborators(Group group, Set<UUID> addedStudentIds, Project project) {
        try {
            String repoFullName = group.getRepository().getFullName();
            String teacherEmail = project.getCreatedBy().getEmail();

            logger.info("Adding {} new students as collaborators to repository: {}", addedStudentIds.size(), repoFullName);

            for (UUID studentId : addedStudentIds) {
                try {
                    User student = userRepository.findById(studentId).orElse(null);
                    if (student != null && student.getGithubUsername() != null && !student.getGithubUsername().trim().isEmpty()) {
                        // Add student as repository collaborator with push permission
                        repositoryService.addCollaborator(repoFullName, student.getGithubUsername(), "push", teacherEmail);
                        logger.info("Successfully added student {} ({}) as collaborator to repository {}",
                            student.getEmail(), student.getGithubUsername(), repoFullName);
                    } else {
                        logger.warn("Student with ID {} not found or has no GitHub username, skipping repository invitation", studentId);
                    }
                } catch (Exception e) {
                    logger.error("Failed to add student with ID {} as repository collaborator: {}", studentId, e.getMessage());
                    // Continue with other students even if one fails
                }
            }
        } catch (Exception e) {
            logger.error("Error adding new students as repository collaborators: {}", e.getMessage());
            // Don't throw exception to avoid breaking the group update process
        }
    }

    @Override
    public void deleteGroup(UUID id) {
        deleteGroup(id, false);
    }

    @Override
    public void deleteGroup(UUID id, boolean deleteRepository) {
        logger.info("Deleting group with ID: {}, deleteRepository: {}", id, deleteRepository);
        
        // First, find the group with its repository
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found with ID: " + id));
        
        tn.esprithub.server.repository.entity.Repository repository = group.getRepository();
        
        // Delete the group first
        groupRepository.deleteById(id);
        
        // If repository exists and deleteRepository is true, delete it
        if (repository != null && deleteRepository) {
            try {
                repositoryEntityService.deleteRepository(repository.getId(), true);
                logger.info("Repository deleted successfully for group: {}", group.getName());
            } catch (Exception e) {
                logger.error("Failed to delete repository for group {}: {}", group.getName(), e.getMessage());
                // Don't throw exception here as group is already deleted
            }
        } else if (repository != null) {
            // Just unlink the repository from the group (repository remains orphaned)
            logger.info("Repository kept but unlinked from group: {}", group.getName());
        }
        
        logger.info("Group deleted successfully: {}", group.getName());
    }

    @Override
    public Group getGroupById(UUID id) {
        Optional<Group> group = groupRepository.findById(id);
        return group.orElse(null);
    }

    @Override
    public List<Group> getAllGroups() {
        return groupRepository.findAll();
    }

    @Override
    public List<Group> getGroupsByProjectId(UUID projectId) {
        return groupRepository.findByProjectId(projectId);
    }

    @Override
    public List<Group> getGroupsByProjectIdAndClasseId(UUID projectId, UUID classeId) {
        return groupRepository.findByProjectIdAndClasseId(projectId, classeId);
    }
}
