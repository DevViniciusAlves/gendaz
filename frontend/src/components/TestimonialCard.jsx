import { Star } from 'lucide-react'

export default function TestimonialCard({ quote, name, segment, avatar }) {
  return (
    <article className="testimonial-card">
      <div className="testimonial-card__stars" aria-label="5 estrelas">
        {Array.from({ length: 5 }).map((_, index) => (
          <Star key={index} size={14} fill="currentColor" strokeWidth={1.75} aria-hidden="true" />
        ))}
      </div>

      <p className="testimonial-card__quote">{quote}</p>

      <div className="testimonial-card__footer">
        <div className="testimonial-card__avatar" aria-hidden="true">
          {avatar}
        </div>
        <div className="testimonial-card__meta">
          <strong>{name}</strong>
          <span>{segment}</span>
        </div>
      </div>
    </article>
  )
}
