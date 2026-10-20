ALTER TABLE public.card_collection
    ALTER COLUMN milestone_points DROP NOT NULL;

ALTER TABLE public.card_collection
    DROP CONSTRAINT IF EXISTS card_collection_student_milestone_unique;

CREATE UNIQUE INDEX IF NOT EXISTS card_collection_student_milestone_unique
    ON public.card_collection (student_id, milestone_points)
    WHERE milestone_points IS NOT NULL;
