-- V1에 빠진 팔로우 테이블을 보완한다. Hibernate가 만든 기존 테이블은 V2에서 그대로 이전한다.
CREATE TABLE IF NOT EXISTS public.p_follows (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    creator_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid NOT NULL,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by uuid NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    CONSTRAINT uk_follows_user_creator UNIQUE (user_id, creator_id),
    CONSTRAINT fk_follows_user FOREIGN KEY (user_id) REFERENCES public.p_users (id),
    CONSTRAINT fk_follows_creator FOREIGN KEY (creator_id) REFERENCES public.p_creators (id),
    CONSTRAINT fk_follows_created_by FOREIGN KEY (created_by) REFERENCES public.p_users (id),
    CONSTRAINT fk_follows_updated_by FOREIGN KEY (updated_by) REFERENCES public.p_users (id),
    CONSTRAINT fk_follows_deleted_by FOREIGN KEY (deleted_by) REFERENCES public.p_users (id)
);
CREATE INDEX IF NOT EXISTS idx_follows_user ON public.p_follows (user_id);
CREATE INDEX IF NOT EXISTS idx_follows_creator ON public.p_follows (creator_id);
