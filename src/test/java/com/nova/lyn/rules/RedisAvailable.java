package com.nova.lyn.rules;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @ClassName RedisAvailable
 * @Description TODO
 * @Author Lyn
 * @Date 2019/3/26 0026 上午 11:32
 * @Version 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RedisAvailable {
}
