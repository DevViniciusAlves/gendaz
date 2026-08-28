import { useEffect } from "react";
import { Crisp } from "crisp-sdk-web";

// Configurações das abas do Crisp (seguindo o escopo solicitado)
const CRISP_TABS_CONFIG = {
  // Chat (Suporte) - Implementado agora
  chat: {
    id: "chat",
    enabled: true,
    icon: "message",
    title: "Suporte",
    subtitle: "Fale com a equipe Gendaz",
  },
  // Central de Ajuda - Deixado pronto (sem conteúdo)
  helpdesk: {
    id: "helpdesk",
    enabled: true,
    icon: "helpdesk",
    title: "Central de Ajuda",
    subtitle: "Em breve", // Sem conteúdo ainda
  },
  // Pesquisa - Deixado pronto (sem conteúdo)
  search: {
    id: "search",
    enabled: true,
    icon: "search",
    title: "Pesquisa",
    subtitle: "Buscar artigos", // Sem conteúdo ainda
  },
};

export function CrispChat() {
  useEffect(() => {
    // Verifica se o Crisp Website ID está configurado no ambiente
    const crispWebsiteId = import.meta.env.VITE_CRISP_WEBSITE_ID;
    if (!crispWebsiteId) {
      console.warn("Crisp Website ID não configurado. Configure VITE_CRISP_WEBSITE_ID no Render.");
      return;
    }

    // Configura o Crisp com o Website ID
    Crisp.configure(crispWebsiteId);

    // Configura as abas conforme solicitado
    Crisp.setWidgetTabs(Object.values(CRISP_TABS_CONFIG).map(tab => ({
      id: tab.id,
      enabled: tab.enabled,
      icon: tab.icon,
      title: tab.title,
      subtitle: tab.subtitle,
    })));

    // Configurações visuais para seguir o padrão Gendaz (preto e branco)
    Crisp.setColor("main", "#FFFFFF"); // Cor principal (branco)
    Crisp.setColor("text", "#111827"); // Texto (preto)
    Crisp.setColor("accent", "#111827"); // Destaque (preto)
    Crisp.setColor("border", "#E5E7EB"); // Bordas (cinza claro)

    // Remove o botão flutuante padrão (opcional, para personalização)
    Crisp.setShowChatButton(false);

    // Limpa o Crisp ao desmontar o componente
    return () => {
      Crisp.reset();
    };
  }, []);

  return null;
}