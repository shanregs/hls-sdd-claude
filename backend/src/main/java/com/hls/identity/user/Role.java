package com.hls.identity.user;

/** The six fixed roles (Constitution Principle II, amended by spec 029). Not creatable, renamable, or deletable. */
public enum Role {
    ADMIN,
    DIRECTOR,
    MANAGER,
    TEACHER,
    SYSTEM,
    /** Authors content and nothing else (spec 029); web only. */
    CONTENT_CONTRIBUTOR
}
