import { useEffect, useState } from "react";
import { MessageCircle } from "lucide-react";

// Integração Crisp (suporte ao cliente) — apenas na landing page.
//
// Abas do chatbox (conforme escopo):
//   1. Chat (Suporte)        -> funcionando (precisa apenas do VITE_CRISP_WEBSITE_ID)
//   2. Central de Ajuda      -> ativar "Helpdesk" no painel do Crisp
//                              (Settings > Setup & Integrations > Chatbox > Helpdesk)
//   3. Pesquisa              -> já vem junto da Central de Ajuda (busca de artigos)
//
// O SDK é carregado dinamicamente dentro do efeito e envolvido em try/catch,
// para que qualquer falha do Crisp NUNCA derrube a landing page (tela preta).
// O estilo preto/branco (padrão Gendaz) é aplicado via setColorTheme.

export function CrispChat() {
  const websiteId = import.meta.env.VITE_CRISP_WEBSITE_ID;
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    if (!websiteId) {
      console.warn(
        "Crisp: VITE_CRISP_WEBSITE_ID não configurado. Defina a variável de ambiente no Render."
      );
      return;
    }

    let cancelled = false;

    (async () => {
      try {
        const { Crisp } = await import("crisp-sdk-web");

        if (cancelled) return;

        Crisp.configure(websiteId, { locale: "pt-br" });
        Crisp.setColorTheme("#111827");

        Crisp.onLoaded(() => {
          if (cancelled) return;
          try {
            window.$crisp.push(["do", "launcher:hide"]);
          } catch {
            /* launcher já pode estar oculto */
          }
          setLoaded(true);
        });
      } catch (err) {
        console.error("Crisp: falha ao inicializar o chatbox", err);
      }
    })();

    return () => {
      cancelled = true;
      try {
        // Remove o Crisp ao desmontar a landing page
        import("crisp-sdk-web").then(({ Crisp }) => Crisp.reset()).catch(() => {});
      } catch {
        /* ignore */
      }
    };
  }, [websiteId]);

  if (!websiteId) return null;

  function openCrisp() {
    import("crisp-sdk-web")
      .then(({ Crisp }) => Crisp.open())
      .catch(() => {});
  }

  return (
    <button
      type="button"
      className="gendaz-crisp-launcher"
      onClick={openCrisp}
      aria-label="Falar com o suporte da Gendaz"
      data-loaded={loaded ? "true" : "false"}
    >
      <MessageCircle size={26} strokeWidth={2} />
      <span className="gendaz-crisp-launcher-pulse" aria-hidden="true" />
    </button>
  );
}
