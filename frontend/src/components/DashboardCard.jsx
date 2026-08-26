export default function DashboardCard({ title, value, detail, icon: Icon }) {
  return (
    <article className="metric-card">
      <div>
        <span>{title}</span>
        <strong>{value}</strong>
        {detail && <small>{detail}</small>}
      </div>
      {Icon && <Icon size={22} />}
    </article>
  )
}
