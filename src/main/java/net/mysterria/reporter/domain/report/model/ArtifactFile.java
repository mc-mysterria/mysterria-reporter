package net.mysterria.reporter.domain.report.model;

import lombok.Data;

@Data
public class ArtifactFile {

    private final String artifactId;
    private final String data;
    private final boolean found = true;
}
