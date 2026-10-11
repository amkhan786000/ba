package com.rahbar.dto;

/** A finished download: file bytes, content type and file name. */
public record ReportFile(byte[] bytes, String contentType, String filename) {}
