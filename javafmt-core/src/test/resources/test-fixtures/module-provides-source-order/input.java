module com.example.providers {
    provides com.example.api.Service with com.example.impl.ZetaImpl, com.example.impl.AlphaImpl, com.example.impl.MuImpl;
}
