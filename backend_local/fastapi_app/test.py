try:
    from mediapipe.python.solutions import face_mesh as mp_face_mesh
    test_mesh = mp_face_mesh.FaceMesh(static_image_mode=True)
    print("✅ SUCCÈS : Le module FaceMesh est bien chargé !")
except Exception as e:
    print(f"❌ ÉCHEC : Toujours une erreur : {e}")