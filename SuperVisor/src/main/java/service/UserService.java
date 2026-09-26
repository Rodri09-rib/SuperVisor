package service;

import domain.dto.CurrentUserDTO;
import domain.model.entities.User;
import domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

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
}
