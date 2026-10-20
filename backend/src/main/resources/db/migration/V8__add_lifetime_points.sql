ALTER TABLE public.user_points
    ADD COLUMN IF NOT EXISTS lifetime_points bigint NOT NULL DEFAULT 0;

UPDATE public.user_points
SET lifetime_points = GREATEST(lifetime_points, points);
