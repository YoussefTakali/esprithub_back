package tn.esprithub.server.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionFileDto {
    
    private UUID id;
    private String fileName;
    private String originalName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private String fileUrl;
    private String content;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Helper methods
    public String getFileExtension() {
        if (originalName == null) return "";
        int lastDot = originalName.lastIndexOf('.');
        return lastDot > 0 ? originalName.substring(lastDot + 1).toLowerCase() : "";
    }
    
    public String getDisplaySize() {
        if (fileSize == null) return "0 B";
        
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        double size = fileSize.doubleValue();
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.1f %s", size, units[unitIndex]);
    }
    
    public boolean isCodeFile() {
        String ext = getFileExtension();
        String[] codeExtensions = {"java", "js", "ts", "py", "cpp", "c", "cs", "php", "rb", "go", "rs", "kt", "swift", "html", "css", "scss", "xml", "json", "yaml", "yml", "sql", "sh", "bat"};
        for (String codeExt : codeExtensions) {
            if (ext.equals(codeExt)) return true;
        }
        return false;
    }
    
    public boolean isImageFile() {
        String ext = getFileExtension();
        String[] imageExtensions = {"png", "jpg", "jpeg", "gif", "svg", "ico", "webp", "bmp"};
        for (String imgExt : imageExtensions) {
            if (ext.equals(imgExt)) return true;
        }
        return false;
    }
    
    public boolean isTextFile() {
        String ext = getFileExtension();
        String[] textExtensions = {"txt", "md", "rtf", "log", "conf", "config", "properties", "ini", "toml"};
        for (String txtExt : textExtensions) {
            if (ext.equals(txtExt)) return true;
        }
        return false;
    }
}
