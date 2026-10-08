package domain.model.entities;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Feriado do calendário.
 *
 * <p>Uma linha por data: o par {@code (date)} é único porque registar duas vez
 * o mesmo dia — por engano, ou por duas pessoas o fazerem — não faria o dia valer
 * mais, e a recompensa de trabalho no feriado passaria a ser ambígua: qual dos
 * dois registos é que manda?
 *
 * <p>É a tabela que o fecho da escala consulta para saber quais os dias que
 * rendem folga extra (ver {@code ScaleRewardsService}). O feriado não é
 * calculado nem vem de nenhuma lista oficial: quem regista é a supervisão, que
 * é quem sabe que dia é festa local.
 */
@Entity
@Table(
        name = "tb_holiday",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_holiday_date",
                columnNames = "date"))
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** O dia do feriado. A coluna chama-se {@code date}, como em todas as datas do arquivo. */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /** Ex.: "N. Senhora da Aparecida". Obrigatório: um feriado sem nome não se distingue de um erro de data. */
    @Column(name = "description", nullable = false, length = 120)
    private String description;

    public Holiday() {

    }

    public Holiday(LocalDate date, String description) {
        this.date = date;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
