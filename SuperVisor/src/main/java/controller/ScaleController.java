package controller;

import domain.dto.CreateScaleDTO;
import domain.dto.ScaleCoverageDTO;
import domain.dto.ScaleDeletionSummaryDTO;
import domain.model.entities.EditionScale;
import security.ProfileAuthorization;
import service.ScaleCoverageService;
import service.ScaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scales")
public class ScaleController {

    @Autowired
    private ScaleService scaleService;

    @Autowired
    private ScaleCoverageService scaleCoverageService;

    @Autowired
    private ProfileAuthorization profileAuthorization;

    @PostMapping
    public ResponseEntity<EditionScale> createScale(@RequestBody CreateScaleDTO dto) {
        EditionScale newScale = scaleService.createScale(dto);

        return ResponseEntity.ok(newScale);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Void> publishScale(@PathVariable Long id) {
        scaleService.publishSchedule(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<EditionScale>> listAll() {

        return ResponseEntity.ok(scaleService.listAll());
    }

    /**
     * Detalhes de uma escala. O {@code id} só aceita dígitos para que um
     * caminho como {@code /api/v1/scales/1/alterar} continue a devolver 404 em
     * vez de cair nesta rota e responder 400 por falha de conversão.
     */
    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<EditionScale> getById(@PathVariable Long id) {

        return ResponseEntity.ok(scaleService.findById(id));
    }

    /**
     * Cobertura e conflitos da escala.
     *
     * <p>Fica na mesma rota de detalhe e com a mesma restrição a dígitos, para
     * que {@code /api/v1/scales/1/coverage} não colida com a rota de detalhe.
     *
     * <p>É uma leitura, como o resto dos recursos de escala, e não fica restrita
     * à supervisão: quem está vendo a escala precisa ver também o que está
     * errado nela. O relatório não expõe o motivo das folgas pelo mesmo motivo.
     */
    @GetMapping("/{id:[0-9]+}/coverage")
    public ResponseEntity<ScaleCoverageDTO> coverage(@PathVariable Long id) {

        return ResponseEntity.ok(scaleCoverageService.relatorio(id));
    }

    /**
     * O que a exclusão de uma escala leva consigo.
     *
     * <p>A exclusão em cascata é a única desta aplicação que apaga registos que
     * ninguém pediu para apagar: uma escala com turnos leva também os pedidos de
     * troca que os referenciam. Expor a contagem antes de perguntar é o que
     * transforma a confirmação numa decisão em vez de um reflexo.
     *
     * <p>É leitura, mas não é leitura como as outras duas desta rota. O relatório
     * de cobertura é aberto a toda a gente autenticada porque é o que serve para
     * todos verem o que está errado na escala; este conta o que a supervisão está
     * a prestes a destruir, e só a supervisão o pode destruir.
     */
    @GetMapping("/{id:[0-9]+}/exclusao")
    public ResponseEntity<ScaleDeletionSummaryDTO> resumoExclusao(@PathVariable Long id) {
        profileAuthorization.exigirSupervisor();

        return ResponseEntity.ok(scaleService.resumoExclusao(id));
    }

    /**
     * Exclui uma escala, os turnos dela e os pedidos de troca que os referenciam.
     *
     * <p>Restrito à supervisão, ao contrário de criar e publicar, que são
     * operações de trabalho e não de administração. A regra de negócio está em
     * {@link ScaleService#apagar} e não aqui: a autorização é da camada web, e o
     * serviço mantém-se responsável apenas pela ordem em que as coisas caem.
     */
    @DeleteMapping("/{id:[0-9]+}")
    public ResponseEntity<Void> apagar(@PathVariable Long id) {
        profileAuthorization.exigirSupervisor();

        scaleService.apagar(id);

        return ResponseEntity.noContent().build();
    }
}