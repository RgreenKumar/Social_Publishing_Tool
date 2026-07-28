import { useEffect, useState } from "react";

const SLIDES = [
  {
    id: "linkedin",
    src: `${process.env.PUBLIC_URL}/slides/linkedin-post.png`,
    label: "LinkedIn",
  },
  {
    id: "instagram",
    src: `${process.env.PUBLIC_URL}/slides/instagram-post.png`,
    label: "Instagram",
  },
  {
    id: "facebook",
    src: `${process.env.PUBLIC_URL}/slides/facebook-post.png`,
    label: "Facebook",
  },
];

export default function LandingSlideshow({ intervalMs = 3500 }) {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const id = window.setInterval(() => {
      setIndex((current) => (current + 1) % SLIDES.length);
    }, intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs]);

  return (
    <div className="landing-slideshow" aria-roledescription="carousel">
      <div className="landing-slideshow__stage">
        {SLIDES.map((slide, i) => (
          <img
            key={slide.id}
            src={slide.src}
            alt={`${slide.label} post preview`}
            className={`landing-slideshow__slide${
              i === index ? " landing-slideshow__slide--active" : ""
            }`}
          />
        ))}
      </div>

      <div className="landing-slideshow__meta">
        <p className="landing__visual-label">Preview</p>
        <p className="landing-slideshow__caption">{SLIDES[index].label}</p>
        <div className="landing-slideshow__dots" role="tablist" aria-label="Slides">
          {SLIDES.map((slide, i) => (
            <button
              key={slide.id}
              type="button"
              role="tab"
              aria-selected={i === index}
              aria-label={`Show ${slide.label}`}
              className={`landing-slideshow__dot${
                i === index ? " landing-slideshow__dot--active" : ""
              }`}
              onClick={() => setIndex(i)}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
