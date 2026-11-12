package io.github.yangorbunov.incidents.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Клиентская маршрутизация React: /incidents/42 при перезагрузке страницы
 * должен отдать index.html, а не 404. API и статика с точкой в имени
 * (main.js, favicon.svg) сюда не попадают.
 */
@Controller
public class SpaController {

    private static final String SEG = "[^.]*";
    private static final String FIRST = "(?!api$|actuator$)[^.]*";

    @GetMapping({
            "/",
            "/{a:" + FIRST + "}",
            "/{a:" + FIRST + "}/{b:" + SEG + "}",
            "/{a:" + FIRST + "}/{b:" + SEG + "}/{c:" + SEG + "}"
    })
    public String index() {
        return "forward:/index.html";
    }
}
