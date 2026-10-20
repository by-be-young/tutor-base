DROP INDEX IF EXISTS public.card_collection_student_milestone_unique;

ALTER TABLE public.card_collection
    DROP CONSTRAINT IF EXISTS card_collection_milestone_positive;

ALTER TABLE public.card_collection
    DROP COLUMN IF EXISTS milestone_points;
