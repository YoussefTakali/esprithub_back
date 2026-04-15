package tn.esprithub.server.project.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SubmissionReviewAppPreviewDto {
    private String framework;
    private String language;
    private String provider;
    private String previewUrl;
    private String source;
    private Double confidence;
    private List<String> detectedSignals;
    private List<String> supportedFrameworks;
    private String repositoryUrl;
}