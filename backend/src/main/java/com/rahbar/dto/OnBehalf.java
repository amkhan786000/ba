package com.rahbar.dto;

/**
 * A sponsorship on behalf of an anonymous donor: the donor's chapter (chapters.chapter_id) and a short label to tell
 * several donors apart (e.g. "Donor A", never a name). null wherever a sponsorship is the sponsor's own.
 */
public record OnBehalf(Long donorChapterId, String label) {}
