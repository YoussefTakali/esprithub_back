package tn.esprithub.server.notification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import tn.esprithub.server.user.entity.User;

@Entity
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String message;
    private String type; // INFO, WARNING, SUCCESS, ERROR
    private LocalDateTime timestamp;
    private boolean isRead;

    @ManyToOne(fetch = FetchType.LAZY)
    private User student;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
} 