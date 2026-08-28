import { useEffect } from "react";
import { Crisp } from "crisp-sdk-web";
import { MessageCircle } from "lucide-react";

// Launcher customizado do Crisp — landing page (Gendaz).
//
// O Crisp já está integrado via script do CloudPages (window.$crisp + window.CRISP_WEBSITE_ID).
// Aqui NÃO alteramos a integração: apenas (1) ocultamos o launcher padrão e
// (2) abrimos o chat via API oficial do crisp-sdk-web.
//
// - launcher:hide  -> comando oficial do Crisp (window.$crisp é uma fila: processado ao carregar)
// - Crisp.chat.open() -> API oficial para abrir o chat
//
// Estilos inline/escopo mantidos neste arquivo (não toca CSS global).

const LAUNCHER_CLASS = "gendaz-crisp-launcher";

export function CrispChat() {
  useEffect(() => {
    // Esconde o launcher padrão do Crisp usando o comando oficial.
    // window.$crisp é uma fila: o comando é aplicado quando o Crisp carrega.
    try {
      window.$crisp = window.$crisp || [];
      window.$crisp.push(["do", "launcher:hide"]);
    } catch {
      /* no-op */
    }
  }, []);

  function openChat() {
    try {
      // Caso já esteja carregado, abre via SDK oficial.
      if (window.$crisp && window.$crisp.is) {
        Crisp.chat.open();
      } else if (window.CRISP_WEBSITE_ID) {
        // Ainda carregando: enfileira a abertura (processada quando o Crisp estiver pronto).
        window.$crisp = window.$crisp || [];
        window.$crisp.push(["do", "chat:open"]);
      }
    } catch {
      /* no-op */
    }
  }

  return (
    <>
      <style>{`
        .${LAUNCHER_CLASS} {
          position: fixed;
          right: 24px;
          bottom: 24px;
          z-index: 9999;
          width: 60px;
          height: 60px;
          border: none;
          border-radius: 50%;
          background: #F75B1E;
          color: #ffffff;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          padding: 0;
          box-shadow: 0 10px 24px rgba(247, 91, 30, 0.35);
          transition: transform 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
        }
        .${LAUNCHER_CLASS}:hover {
          transform: translateY(-2px) scale(1.04);
          background: #e14d12;
          box-shadow: 0 14px 30px rgba(247, 91, 30, 0.45);
        }
        .${LAUNCHER_CLASS}:focus-visible {
          outline: none;
          box-shadow: 0 0 0 3px rgba(247, 91, 30, 0.35), 0 10px 24px rgba(247, 91, 30, 0.35);
        }
        @media (max-width: 640px) {
          .${LAUNCHER_CLASS} {
            right: 16px;
            bottom: 16px;
            width: 54px;
            height: 54px;
          }
        }
      `}</style>

      <button
        type="button"
        className={LAUNCHER_CLASS}
        onClick={openChat}
        aria-label="Falar com o suporte da Gendaz"
      >
        <MessageCircle size={26} strokeWidth={2} color="#ffffff" />
      </button>
    </>
  );
}
