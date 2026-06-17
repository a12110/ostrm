package com.hienao.openlist2strm.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** STRM 目录再次遍历服务。 */
@Slf4j
@Service
public class StrmRescanService {

  /** 再次遍历 STRM 目录并输出统计信息。 */
  public StrmRescanResult rescanStrmDirectory(String strmBasePath) {
    if (!StringUtils.hasText(strmBasePath)) {
      log.warn("STRM基础路径为空，跳过再次遍历");
      return StrmRescanResult.empty(strmBasePath);
    }

    Path strmPath = Paths.get(strmBasePath);
    if (!isReadableDirectory(strmPath)) {
      log.info("STRM目录不存在、不可访问或不是目录，跳过再次遍历: {}", strmPath);
      return StrmRescanResult.empty(strmBasePath);
    }

    StrmRescanCounters counters = scanStrmPath(strmPath, strmBasePath);
    StrmRescanResult result = counters.toResult(strmBasePath);
    logResult(result);
    return result;
  }

  private StrmRescanCounters scanStrmPath(Path strmPath, String strmBasePath) {
    StrmRescanCounters counters = new StrmRescanCounters();
    try (java.util.stream.Stream<Path> pathStream = Files.walk(strmPath)) {
      pathStream.filter(path -> !path.equals(strmPath)).forEach(counters::accept);
    } catch (IOException | UncheckedIOException | SecurityException e) {
      log.warn("再次遍历STRM目录失败: {}, 错误: {}", strmBasePath, e.getMessage());
    }
    return counters;
  }

  private boolean isReadableDirectory(Path path) {
    try {
      return Files.exists(path) && Files.isDirectory(path);
    } catch (SecurityException e) {
      log.warn("STRM目录不可访问，跳过再次遍历: {}, 错误: {}", path, e.getMessage());
      return false;
    }
  }

  private void logResult(StrmRescanResult result) {
    log.info(
        "再次遍历STRM目录完成: path={}, directories={}, strmFiles={}, scrapingFiles={}, otherFiles={}",
        result.strmBasePath(),
        result.directoryCount(),
        result.strmFileCount(),
        result.scrapingFileCount(),
        result.otherFileCount());
  }

  private boolean isStrmFile(Path path) {
    return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".strm");
  }

  private boolean isScrapingMetadataFile(Path path) {
    String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return fileName.endsWith(".nfo")
        || fileName.endsWith(".jpg")
        || fileName.endsWith(".jpeg")
        || fileName.endsWith(".png")
        || fileName.endsWith(".webp")
        || fileName.endsWith(".bmp")
        || fileName.endsWith(".xml");
  }

  public record StrmRescanResult(
      String strmBasePath,
      int directoryCount,
      int strmFileCount,
      int scrapingFileCount,
      int otherFileCount) {

    public static StrmRescanResult empty(String strmBasePath) {
      return new StrmRescanResult(strmBasePath, 0, 0, 0, 0);
    }
  }

  private class StrmRescanCounters {
    private final AtomicInteger directoryCount = new AtomicInteger();
    private final AtomicInteger strmFileCount = new AtomicInteger();
    private final AtomicInteger scrapingFileCount = new AtomicInteger();
    private final AtomicInteger otherFileCount = new AtomicInteger();

    private void accept(Path path) {
      try {
        if (Files.isDirectory(path)) {
          directoryCount.incrementAndGet();
        } else if (isStrmFile(path)) {
          strmFileCount.incrementAndGet();
        } else if (isScrapingMetadataFile(path)) {
          scrapingFileCount.incrementAndGet();
        } else {
          otherFileCount.incrementAndGet();
        }
      } catch (SecurityException e) {
        otherFileCount.incrementAndGet();
        log.debug("再次遍历STRM时跳过不可访问路径: {}, 错误: {}", path, e.getMessage());
      }
    }

    private StrmRescanResult toResult(String strmBasePath) {
      return new StrmRescanResult(
          strmBasePath,
          directoryCount.get(),
          strmFileCount.get(),
          scrapingFileCount.get(),
          otherFileCount.get());
    }
  }
}
