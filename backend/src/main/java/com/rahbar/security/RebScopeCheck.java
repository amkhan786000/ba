package com.rahbar.security;

import org.springframework.stereotype.Component;

/** For @PreAuthorize: "!@rebScopeCheck.teacher()" keeps exam set-up and publishing with the office. */
@Component("rebScopeCheck")
public class RebScopeCheck {

    /** True for users whose REB access covers only the classes they teach. */
    public boolean teacher() {
        return Access.rebClassesScoped();
    }
}
