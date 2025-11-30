package tn.esprithub.server.academic.util.helper;

import org.springframework.stereotype.Component;
import tn.esprithub.server.academic.entity.Departement;
import tn.esprithub.server.academic.entity.Niveau;
import tn.esprithub.server.academic.entity.Classe;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Helper class for calculating academic statistics
 */
@Component
public class AcademicStatisticsHelper {

    // Statistics keys constants
    private static final String TOTAL_NIVEAUX = "totalNiveaux";
    private static final String TOTAL_CLASSES = "totalClasses";
    private static final String TOTAL_ETUDIANTS = "totalEtudiants";
    private static final String TOTAL_ENSEIGNANTS = "totalEnseignants";
    private static final String TOTAL_DEPARTEMENTS = "totalDepartements";
    private static final String CAPACITE_RESTANTE = "capaciteRestante";
    private static final String AVERAGE_OCCUPANCY_RATE = "averageOccupancyRate";
    private static final String MIN_OCCUPANCY_RATE = "minOccupancyRate";
    private static final String MAX_OCCUPANCY_RATE = "maxOccupancyRate";

    public Map<String, Integer> calculateDepartementStatistics(Departement departement) {
        Map<String, Integer> stats = new HashMap<>();
        
        if (departement.getNiveaux() == null) {
            stats.put(TOTAL_NIVEAUX, 0);
            stats.put(TOTAL_CLASSES, 0);
            stats.put(TOTAL_ETUDIANTS, 0);
        } else {
            stats.put(TOTAL_NIVEAUX, departement.getNiveaux().size());
            
            int totalClasses = departement.getNiveaux().stream()
                    .mapToInt(niveau -> niveau.getClasses() != null ? niveau.getClasses().size() : 0)
                    .sum();
            stats.put(TOTAL_CLASSES, totalClasses);

            int totalEtudiants = departement.getNiveaux().stream()
                    .flatMap(niveau -> niveau.getClasses() != null ? niveau.getClasses().stream() : java.util.stream.Stream.empty())
                    .mapToInt(classe -> classe.getStudents() != null ? classe.getStudents().size() : 0)
                    .sum();
            stats.put(TOTAL_ETUDIANTS, totalEtudiants);
        }
        
        if (departement.getTeachers() != null) {
            stats.put(TOTAL_ENSEIGNANTS, departement.getTeachers().size());
        } else {
            stats.put(TOTAL_ENSEIGNANTS, 0);
        }
        
        return stats;
    }

    public Map<String, Integer> calculateNiveauStatistics(Niveau niveau) {
        Map<String, Integer> stats = new HashMap<>();
        
        if (niveau.getClasses() == null) {
            stats.put("totalClasses", 0);
            stats.put("totalEtudiants", 0);
        } else {
            stats.put("totalClasses", niveau.getClasses().size());
            
            int totalEtudiants = niveau.getClasses().stream()
                    .mapToInt(classe -> classe.getStudents() != null ? classe.getStudents().size() : 0)
                    .sum();
            stats.put("totalEtudiants", totalEtudiants);
        }
        
        return stats;
    }

    public Map<String, Integer> calculateClasseStatistics(Classe classe) {
        Map<String, Integer> stats = new HashMap<>();
        
        stats.put("totalEtudiants", classe.getStudents() != null ? classe.getStudents().size() : 0);
        stats.put("totalEnseignants", classe.getTeachers() != null ? classe.getTeachers().size() : 0);
        stats.put("capaciteRestante", classe.getCapacite() - (classe.getStudents() != null ? classe.getStudents().size() : 0));
        
        return stats;
    }

    public Map<String, Integer> calculateGlobalStatistics(List<Departement> departements) {
        Map<String, Integer> stats = new HashMap<>();
        
        stats.put("totalDepartements", departements.size());
        
        int totalNiveaux = departements.stream()
                .mapToInt(dept -> dept.getNiveaux() != null ? dept.getNiveaux().size() : 0)
                .sum();
        stats.put("totalNiveaux", totalNiveaux);
        
        int totalClasses = departements.stream()
                .flatMap(dept -> dept.getNiveaux() != null ? dept.getNiveaux().stream() : java.util.stream.Stream.empty())
                .mapToInt(niveau -> niveau.getClasses() != null ? niveau.getClasses().size() : 0)
                .sum();
        stats.put("totalClasses", totalClasses);
        
        int totalEtudiants = departements.stream()
                .flatMap(dept -> dept.getNiveaux() != null ? dept.getNiveaux().stream() : java.util.stream.Stream.empty())
                .flatMap(niveau -> niveau.getClasses() != null ? niveau.getClasses().stream() : java.util.stream.Stream.empty())
                .mapToInt(classe -> classe.getStudents() != null ? classe.getStudents().size() : 0)
                .sum();
        stats.put("totalEtudiants", totalEtudiants);
        
        int totalEnseignants = departements.stream()
                .mapToInt(dept -> dept.getTeachers() != null ? dept.getTeachers().size() : 0)
                .sum();
        stats.put("totalEnseignants", totalEnseignants);
        
        return stats;
    }

    public double calculateOccupancyRate(Classe classe) {
        if (classe.getCapacite() == 0) {
            return 0.0;
        }
        
        int currentStudents = classe.getStudents() != null ? classe.getStudents().size() : 0;
        return (double) currentStudents / classe.getCapacite() * 100.0;
    }

    public Map<String, Double> calculateDepartementOccupancyRates(Departement departement) {
        Map<String, Double> rates = new HashMap<>();
        
        if (departement.getNiveaux() == null) {
            rates.put("averageOccupancyRate", 0.0);
            return rates;
        }
        
        List<Classe> allClasses = departement.getNiveaux().stream()
                .flatMap(niveau -> niveau.getClasses() != null ? niveau.getClasses().stream() : java.util.stream.Stream.empty())
                .toList();
        
        if (allClasses.isEmpty()) {
            rates.put("averageOccupancyRate", 0.0);
            return rates;
        }
        
        double averageRate = allClasses.stream()
                .mapToDouble(this::calculateOccupancyRate)
                .average()
                .orElse(0.0);
        
        rates.put("averageOccupancyRate", averageRate);
        
        double minRate = allClasses.stream()
                .mapToDouble(this::calculateOccupancyRate)
                .min()
                .orElse(0.0);
        rates.put("minOccupancyRate", minRate);
        
        double maxRate = allClasses.stream()
                .mapToDouble(this::calculateOccupancyRate)
                .max()
                .orElse(0.0);
        rates.put("maxOccupancyRate", maxRate);
        
        return rates;
    }
}
