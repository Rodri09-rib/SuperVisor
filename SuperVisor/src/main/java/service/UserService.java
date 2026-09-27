package service;

import domain.dto.ActiveUserDTO;
import domain.dto.CreateUserRequestDTO;
import domain.dto.CurrentUserDTO;
import domain.model.entities.User;
import domain.repository.UserRepository;
import exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Perfil do utilizador autenticado. O e-mail vem do token, já validado
     * pelo {@code SecurityFilter}, pelo que a autenticação está garantida.
     */
    @Transactional(readOnly = true)
    public CurrentUserDTO currentUser(String email) {
        UserDetails encontrado = userRepository.findByEmail(email);
        if (encontrado == null) {
            throw new UsernameNotFoundException("Utilizador não encontrado");
        }
        return CurrentUserDTO.from((User) encontrado);
    }

    /**
     * Utilizadores ativos, por ordem de nome. É a origem de dados do selector
     * de pessoas do editor de alocações: só contas ativas podem receber turnos.
     */
    @Transactional(readOnly = true)
    public List<ActiveUserDTO> listarAtivos() {
        return ActiveUserDTO.from(userRepository.findByActiveTrueOrderByNameAsc());
    }

    /**
     * Cadastra um novo utilizador.
     *
     * <p>O e-mail é a identidade da conta e tem de ser único. A verificação é
     * feita aqui e não deixada para a restrição {@code unique} da base de dados
     * porque essa chegaria como 500: o frontend precisa de um 400 com mensagem
     * para mostrar no formulário, e o utilizador precisa de saber que o e-mail
     * é que está repetido e não o nome ou a password.
     *
     * <p>O e-mail é guardado tal como foi enviado, sem normalização. A
     * autenticação compara o valor por igualdade exata, pelo que passar a
     * gravar em minúsculas faria o valor gravado divergir da chave de leitura
     * e o novo utilizador não conseguiria entrar com o mesmo endereço com que
     * se cadastrou. A unicidade é, por isso, a da coluna {@code email}.
     *
     * <p>A password é encriptada com o {@code BCryptPasswordEncoder} declarado
     * no {@code SecurityConfig}, e nunca chega ao banco em claro.
     */
    @Transactional
    public ActiveUserDTO criar(CreateUserRequestDTO dto) {
        if (userRepository.findByEmail(dto.email()) != null) {
            throw new RegraDeNegocioException("E-mail já cadastrado.");
        }

        User novo = new User();
        novo.setName(dto.name().trim());
        novo.setEmail(dto.email());
        novo.setPassword(passwordEncoder.encode(dto.password()));
        novo.setProfile(dto.profile());
        novo.setActive(dto.active());

        return ActiveUserDTO.from(userRepository.save(novo));
    }
}
