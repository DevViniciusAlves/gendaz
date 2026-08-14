package com.minhaempresa.gendaz.usuario.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.enums.StatusUsuario;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = UsuarioRepositoryTest.JpaTestConfig.class)
class UsuarioRepositoryTest {
    @Configuration
    @EnableJpaRepositories(basePackageClasses = UsuarioRepository.class)
    @EntityScan(basePackages = "com.minhaempresa.gendaz")
    static class JpaTestConfig {
    }

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByIdComEmpresaCarregaEmpresaInicializada() {
        EmpresaEntity empresa = EmpresaEntity.builder()
                .nomeFantasia("Empresa Teste")
                .documento("12345678000190")
                .email("empresa@gendaz.test")
                .status(StatusEmpresa.ATIVA)
                .build();
        entityManager.persist(empresa);

        UsuarioEntity usuario = UsuarioEntity.builder()
                .nome("Usuario Teste")
                .email("usuario@gendaz.test")
                .senha("hash")
                .perfil(PerfilUsuario.DONO)
                .status(StatusUsuario.ATIVO)
                .empresa(empresa)
                .aceitouTermos(true)
                .build();
        entityManager.persist(usuario);
        entityManager.flush();
        entityManager.clear();

        UsuarioEntity encontrado = usuarioRepository.findByIdComEmpresa(usuario.getId()).orElseThrow();

        assertNotNull(encontrado.getEmpresa());
        assertTrue(Hibernate.isInitialized(encontrado.getEmpresa()));
        assertEquals(StatusEmpresa.ATIVA, encontrado.getEmpresa().getStatus());
    }
}
