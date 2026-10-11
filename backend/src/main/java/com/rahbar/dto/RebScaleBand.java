package com.rahbar.dto;

import java.math.BigDecimal;

/** A grade band (also used when a session has no scale yet). */
public record RebScaleBand(BigDecimal min, String grade, String remark, boolean pass) {}
