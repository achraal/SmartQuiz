-- Vérifie ou crée la table avec le bon type
CREATE TABLE IF NOT EXISTS fraud_logs (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid REFERENCES profiles(id),
    type text,
    reason text,
    metadata jsonb, -- TRÈS IMPORTANT
    created_at timestamptz DEFAULT now()
);