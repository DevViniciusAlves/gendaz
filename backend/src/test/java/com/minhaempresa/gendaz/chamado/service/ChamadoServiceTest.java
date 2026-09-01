package com.minhaempresa.gendaz.chamado.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.minhaempresa.gendaz.admin.service.AdminAuditService;
import com.minhaempresa.gendaz.auditoria.service.LogAtividadeService;
import com.minhaempresa.gendaz.chamado.dto.ChamadoDtos.CriarChamadoRequest;
import com.minhaempresa.gendaz.chamado.entity.ChamadoEntity;
import com.minhaempresa.gendaz.chamado.enums.PrioridadeChamado;
import com.minhaempresa.gendaz.chamado.enums.StatusChamado;
import com.minhaempresa.gendaz.chamado.repository.ChamadoRepository;
import com.minhaempresa.gendaz.empresa.entity.EmpresaEntity;
import com.minhaempresa.gendaz.empresa.enums.StatusEmpresa;
import com.minhaempresa.gendaz.shared.BusinessException;
import com.minhaempresa.gendaz.usuario.entity.UsuarioEntity;
import com.minhaempresa.gendaz.usuario.enums.PerfilUsuario;
import com.minhaempresa.gendaz.usuario.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChamadoServiceTest {

    private ChamadoRepository chamadoRepository;
    private UsuarioRepository usuarioRepository;
    private ChamadoService chamadoService;

    private EmpresaEntity empresaA;
    private UsuarioEntity usuarioA;

    @BeforeEach
    void setUp() {
        chamadoRepository = mock(ChamadoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        chamadoService = new ChamadoService(
                chamadoRepository,
                usuarioRepository,
                mock(AdminAuditService.class),
                mock(LogAtividadeService.class)
        );

        empresaA = EmpresaEntity.builder()
                .id(100L)
                .nomeFantasia("Empresa A")
                .email("contato@empresa-a.com")
                .status(StatusEmpresa.ATIVA)
                .build();
        usuarioA = UsuarioEntity.builder()
                .id(200L)
                .nome("Dono A")
                .email("dono@empresa-a.com")
                .perfil(PerfilUsuario.DONO)
                .empresa(empresaA)
                .build();
    }

    private ChamadoEntity salvarComoMock(ChamadoEntity entity) {
        when(chamadoRepository.save(any(ChamadoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return entity;
    }

    @Test
    void aceitarChamadoDeAlteracaoDeContaCriaUmUnicoRegistroComStatusAberto() {
        when(usuarioRepository.findById(200L)).thenReturn(Optional.of(usuarioA));
        salvarComoMock(null);

        var response = chamadoService.criar(
                new CriarChamadoRequest("Alteração em conta", PrioridadeChamado.MEDIA,
                        "Preciso alterar meu e-mail para novo@empresa.com"),
                200L
        );

        assertEquals("Alteração em conta", response.assunto());
        assertEquals(StatusChamado.ABERTO, response.status());
        assertEquals(100L, response.empresaId());
        assertEquals(200L, response.usuarioId());
        assertEquals("PAINEL", response.origem());
        verify(chamadoRepository, times(1)).save(any(ChamadoEntity.class));
    }

    @Test
    void chamadoCriadoEOMesmoQueApareceParausuarioEAdmin() {
        when(usuarioRepository.findById(200L)).thenReturn(Optional.of(usuarioA));
        when(chamadoRepository.save(any(ChamadoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var criado = chamadoService.criar(
                new CriarChamadoRequest("Alteração em conta", PrioridadeChamado.MEDIA, "Quero trocar o nome fantasia"),
                200L
        );

        when(chamadoRepository.findByEmpresaIdOrderByDataCriacaoDesc(100L)).thenReturn(
                List.of(ChamadoEntity.builder()
                        .id(criado.id())
                        .assunto(criado.assunto())
                        .mensagem(criado.mensagem())
                        .prioridade(criado.prioridade())
                        .origem(criado.origem())
                        .empresa(empresaA)
                        .usuario(usuarioA)
                        .status(criado.status())
                        .build())
        );
        when(chamadoRepository.findAllByOrderByDataCriacaoDesc()).thenReturn(
                List.of(ChamadoEntity.builder()
                        .id(criado.id())
                        .assunto(criado.assunto())
                        .mensagem(criado.mensagem())
                        .prioridade(criado.prioridade())
                        .origem(criado.origem())
                        .empresa(empresaA)
                        .usuario(usuarioA)
                        .status(criado.status())
                        .build())
        );

        var noSuporte = chamadoService.listarPorEmpresa(100L, 200L);
        var noAdmin = chamadoService.listarTodos();

        assertEquals(1, noSuporte.size());
        assertEquals(criado.id(), noSuporte.get(0).id());
        assertEquals(criado.assunto(), noSuporte.get(0).assunto());
        assertEquals(1, noAdmin.size());
        assertEquals(criado.id(), noAdmin.get(0).id());
        assertEquals(criado.assunto(), noAdmin.get(0).assunto());
    }

    @Test
    void usuarioDeOutraEmpresaNaoListaChamados() {
        EmpresaEntity empresaB = EmpresaEntity.builder()
                .id(300L)
                .nomeFantasia("Empresa B")
                .status(StatusEmpresa.ATIVA)
                .build();
        UsuarioEntity usuarioB = UsuarioEntity.builder()
                .id(400L)
                .nome("Dono B")
                .perfil(PerfilUsuario.DONO)
                .empresa(empresaB)
                .build();
        when(usuarioRepository.findById(400L)).thenReturn(Optional.of(usuarioB));

        BusinessException ex = assertThrows(BusinessException.class, () -> chamadoService.listarPorEmpresa(100L, 400L));
        assertEquals("Acesso nao autorizado aos chamados desta empresa.", ex.getMessage());
        verify(chamadoRepository, never()).findByEmpresaIdOrderByDataCriacaoDesc(100L);
    }
}