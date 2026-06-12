package com.minidoodle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

// VIA_DTO serializes Page responses through a stable PagedModel JSON shape
// ({content, page:{size,number,totalElements,totalPages}}) instead of
// PageImpl's internal representation, which Spring does not guarantee
// across versions and warns about at runtime.
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class MiniDoodleApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniDoodleApplication.class, args);
    }

}
