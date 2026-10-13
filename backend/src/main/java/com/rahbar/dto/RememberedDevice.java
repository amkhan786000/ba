package com.rahbar.dto;

/** A browser just remembered after the emailed sign-in code: its token (for the cookie) and for how many days. */
public record RememberedDevice(String token, int days) {}
