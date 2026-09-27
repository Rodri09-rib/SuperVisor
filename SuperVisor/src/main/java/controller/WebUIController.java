package controller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebUIController {

    @GetMapping("/login")
    public String loginPage(){
        return "login";
    }

    /**
     * Dashboard principal.
     *
     * <p>Devolve {@code "dashboard"} e não {@code "Dashboard"}: o resolvedor de
     * vistas procura o ficheiro pelo nome exacto, e a comparação não é
     * normalizada. Funcionava em Windows, onde o sistema de ficheiros ignora as
     * maiúsculas, e falhava em Linux e em qualquer contentor, que não ignoram —
     * o mesmo código passava o teste em casa e dava 500 no servidor.
     */
    @GetMapping("/dashboard")
    public String dashboardPage(){
        return "dashboard";
    }

    /** Histórico de trocas de turno. */
    @GetMapping("/exchanges")
    public String exchangesPage(){
        return "exchanges";
    }

    /** Escala semanal de presencialidade / home office por equipa. */
    @GetMapping("/work-modality")
    public String workModalityPage(){
        return "work-modality";
    }

    /** Registo e consulta de folgas. */
    @GetMapping("/leaves")
    public String leavesPage(){
        return "leaves";
    }
}
