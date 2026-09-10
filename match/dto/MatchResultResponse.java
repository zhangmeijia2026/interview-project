package com.group5.interview.module.match.dto;

import java.util.List;

/** 匹配详情对外返回（POST/GET /match）。 */
public record MatchResultResponse(int overall, int skill, int experience, int education,
                                  List<GapItem> gap, List<FocusItem> focus, String summary) {
}
