package com.mirattic.flow.issue.dto;

import com.mirattic.flow.issue.entity.IssuePriority;

public record PriorityCount(IssuePriority priority, long count) {
}
