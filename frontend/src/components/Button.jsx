export default function Button({ children, variant = 'primary', icon: Icon, className = '', type = 'button', ...props }) {
  return (
    <button type={type} className={`btn btn-${variant} ${className}`.trim()} {...props}>
      {Icon && <Icon size={17} />}
      <span>{children}</span>
    </button>
  )
}
