type BrandProps = {
  compact?: boolean
}

export function Brand({ compact = false }: BrandProps) {
  return (
    <div className="brand" aria-label="AI Order & Delivery Agent">
      <span className="brand__mark" aria-hidden="true">
        OA
      </span>
      {!compact && <span className="brand__name">Order Agent</span>}
    </div>
  )
}
