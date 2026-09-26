package controller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebUIController {

    @GetMapping("/login")
    public String loginPage(){
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboardPage(){
        return "Dashboard";
    }
}
