package com.example.redisdemo1.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author lh
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private String name;

    private Integer age;
}
