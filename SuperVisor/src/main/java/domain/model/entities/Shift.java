package domain.model.entities;

import jakarta.persistence.*;

import java.time.LocalTime;

@Entity
@Table(name = "tb_shift")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String acronym;
    private LocalTime startTime;
    private LocalTime endTime;
    private String dayiftheWeek;

    public Shift(){

    }

    public Shift(Long id, String acronym, LocalTime startTime, LocalTime endTime, String dayiftheWeek) {
        this.id = id;
        this.acronym = acronym;
        this.startTime = startTime;
        this.endTime = endTime;
        this.dayiftheWeek = dayiftheWeek;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAcronym() {
        return acronym;
    }

    public void setAcronym(String acronym) {
        this.acronym = acronym;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public String getDayiftheWeek() {
        return dayiftheWeek;
    }

    public void setDayiftheWeek(String dayiftheWeek) {
        this.dayiftheWeek = dayiftheWeek;
    }
}
