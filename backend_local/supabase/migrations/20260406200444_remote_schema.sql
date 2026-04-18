


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE EXTENSION IF NOT EXISTS "pg_graphql" WITH SCHEMA "graphql";






CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";






CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";






CREATE OR REPLACE FUNCTION "public"."handle_new_user"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
  INSERT INTO public.profiles (id, username, email, best_score)
  VALUES (
    new.id, 
    new.raw_user_meta_data->>'username', 
    new.email, 
    0
  );
  RETURN new;
END;
$$;


ALTER FUNCTION "public"."handle_new_user"() OWNER TO "postgres";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "public"."monitoring" (
    "id" integer NOT NULL,
    "user_id" "uuid",
    "latitude" double precision,
    "longitude" double precision,
    "captured_at" timestamp with time zone DEFAULT "now"(),
    "address" "text"
);


ALTER TABLE "public"."monitoring" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."monitoring_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."monitoring_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "public"."monitoring_id_seq" OWNED BY "public"."monitoring"."id";



CREATE TABLE IF NOT EXISTS "public"."profiles" (
    "id" "uuid" NOT NULL,
    "username" "text" NOT NULL,
    "email" "text",
    "best_score" integer DEFAULT 0
);


ALTER TABLE "public"."profiles" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."quizzes" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "question_text" "text" NOT NULL,
    "option_a" "text" NOT NULL,
    "option_b" "text" NOT NULL,
    "option_c" "text" NOT NULL,
    "option_d" "text" NOT NULL,
    "correct_option" "text" NOT NULL,
    "level_number" integer NOT NULL,
    "image_url" "text"
);


ALTER TABLE "public"."quizzes" OWNER TO "postgres";


ALTER TABLE ONLY "public"."monitoring" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."monitoring_id_seq"'::"regclass");



ALTER TABLE ONLY "public"."monitoring"
    ADD CONSTRAINT "monitoring_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."profiles"
    ADD CONSTRAINT "profiles_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."quizzes"
    ADD CONSTRAINT "quizzes_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."monitoring"
    ADD CONSTRAINT "monitoring_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "auth"."users"("id");



ALTER TABLE ONLY "public"."profiles"
    ADD CONSTRAINT "profiles_id_fkey" FOREIGN KEY ("id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;



CREATE POLICY "Admin can insert quizzes" ON "public"."quizzes" FOR INSERT TO "authenticated" WITH CHECK ((("auth"."jwt"() ->> 'email'::"text") = 'admin@emsi.ma'::"text"));



CREATE POLICY "Admin can read monitoring" ON "public"."monitoring" FOR SELECT TO "authenticated" USING (true);



CREATE POLICY "Allow public read access" ON "public"."quizzes" FOR SELECT USING (true);



CREATE POLICY "Can insert own monitoring" ON "public"."monitoring" FOR INSERT TO "authenticated" WITH CHECK (("auth"."uid"() = "user_id"));



CREATE POLICY "L'admin peut lire tous les profils" ON "public"."profiles" FOR SELECT TO "authenticated" USING ((("auth"."jwt"() ->> 'email'::"text") = 'admin@emsi.ma'::"text"));



CREATE POLICY "Les utilisateurs peuvent modifier leur propre score" ON "public"."profiles" FOR UPDATE TO "authenticated" USING (("auth"."uid"() = "id")) WITH CHECK (("auth"."uid"() = "id"));



CREATE POLICY "Les utilisateurs peuvent voir leur propre profil" ON "public"."profiles" FOR SELECT TO "authenticated" USING (("auth"."uid"() = "id"));



CREATE POLICY "Les utilisateurs voient leur propre profil" ON "public"."profiles" FOR SELECT TO "authenticated" USING (("auth"."uid"() = "id"));



ALTER TABLE "public"."monitoring" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."profiles" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."quizzes" ENABLE ROW LEVEL SECURITY;




ALTER PUBLICATION "supabase_realtime" OWNER TO "postgres";


GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";

























































































































































GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "service_role";


















GRANT ALL ON TABLE "public"."monitoring" TO "anon";
GRANT ALL ON TABLE "public"."monitoring" TO "authenticated";
GRANT ALL ON TABLE "public"."monitoring" TO "service_role";



GRANT ALL ON SEQUENCE "public"."monitoring_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."monitoring_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."monitoring_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."profiles" TO "anon";
GRANT ALL ON TABLE "public"."profiles" TO "authenticated";
GRANT ALL ON TABLE "public"."profiles" TO "service_role";



GRANT ALL ON TABLE "public"."quizzes" TO "anon";
GRANT ALL ON TABLE "public"."quizzes" TO "authenticated";
GRANT ALL ON TABLE "public"."quizzes" TO "service_role";









ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "service_role";































drop extension if exists "pg_net";

CREATE TRIGGER on_auth_user_created AFTER INSERT ON auth.users FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();


  create policy "Admin can read photos"
  on "storage"."objects"
  as permissive
  for select
  to authenticated
using (((bucket_id = 'monitoring'::text) AND ((auth.jwt() ->> 'email'::text) = 'admin@emsi.ma'::text)));



  create policy "Admin peut voir tout le monitoring"
  on "storage"."objects"
  as permissive
  for select
  to authenticated
using (((bucket_id = 'monitoring'::text) AND ((auth.jwt() ->> 'email'::text) = 'admin@emsi.ma'::text)));



  create policy "ImagePolicy 1ef46i8_0"
  on "storage"."objects"
  as permissive
  for select
  to authenticated
using (((bucket_id = 'quiz-images'::text) AND (auth.role() = 'authenticated'::text)));



  create policy "MonitPolicy umopc8_0"
  on "storage"."objects"
  as permissive
  for insert
  to authenticated
with check (((bucket_id = 'monitoring'::text) AND (auth.role() = 'authenticated'::text)));



