package com.rahbar.dto;

/** One addressee: a user (userId set) or a chapter lead's own email address. */
public record BroadcastRecipient(Long userId, String name, String email) {}
