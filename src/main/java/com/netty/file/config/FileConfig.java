package com.netty.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author guandezhi
 * @ClassName: com.netty.file.config
 * @Description:
 * @date 2018/4/10 15:30
 */
@Component
@ConfigurationProperties(prefix = "file")
public class FileConfig {

    private String uploadDir;

    private String tmpDir;

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getTmpDir() {
        return tmpDir;
    }

    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
    }
}
