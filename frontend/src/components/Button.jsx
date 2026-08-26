import { Loader } from 'lucide-react'

export default function Button({ children, variant = 'primary', icon: Icon, className = '', type = 'button', loading = false, loadingText = 'Carregando...', disabled = false, ...props }) {
  const isDisabled = disabled || loading
  return (
    <button type={type} className={`btn btn-${variant} ${className} ${loading ? 'btn-loading' : ''}`.trim()} disabled={isDisabled} {...props}>
      {loading ? <Loader className="spin" size={17} /> : Icon && <Icon size={17} />}
      <span>{loading ? loadingText : children}</span>
    </button>
  )
}