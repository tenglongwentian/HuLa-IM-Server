package com.hula.core.user.domain.vo.req.user;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author nyh
 */
@Data
public class LoginReq {

    @NotNull
    private String name;

    @NotNull
    private String password;
}
