package com.rahbar.dto;

/** A file to attach: shown to the recipient as name, read from path. */
public record EmailAttachment(String name, java.nio.file.Path path) {}
