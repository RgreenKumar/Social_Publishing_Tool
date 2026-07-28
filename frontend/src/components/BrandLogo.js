export default function BrandLogo({ className = "", size = 36, showText = true }) {
  return (
    <span className={`brand-logo ${className}`.trim()}>
      <img
        src={`${process.env.PUBLIC_URL}/logo.png`}
        alt="PostFusion"
        width={size}
        height={size}
        className="brand-logo__img"
      />
      {showText && <span className="brand-logo__text">PostFusion</span>}
    </span>
  );
}
