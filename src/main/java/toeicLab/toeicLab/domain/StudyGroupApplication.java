package toeicLab.toeicLab.domain;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StudyGroupApplication {

    @Id @GeneratedValue
    private Long id;

    @OneToOne
    private Member member;

    @ElementCollection
    private List<StudyGroupApplicationTag> tags;

    private LocalDateTime submitTime;

    private boolean matching;

    private int value;
}
