-- 添加任务级开关：刮削后是否再次遍历 STRM 目录
ALTER TABLE task_config ADD COLUMN rescan_strm_after_scraping INTEGER DEFAULT 0;
