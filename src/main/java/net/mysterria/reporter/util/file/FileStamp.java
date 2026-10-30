package net.mysterria.reporter.util.file;

import lombok.Data;

@Data
public class FileStamp {
    private final long modifiedAt;
    private final long size;
}
