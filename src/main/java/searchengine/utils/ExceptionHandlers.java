package searchengine.utils;

import org.jsoup.HttpStatusException;
import searchengine.dto.SiteStatus;
import searchengine.models.SiteEntity;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.net.SocketTimeoutException;

public class ExceptionHandlers {

    private final SiteEntity siteEntity;
    private final SiteRepository siteRepository;

    public ExceptionHandlers(SiteEntity siteEntity, SiteRepository siteRepository) {
        this.siteEntity = siteEntity;
        this.siteRepository = siteRepository;
    }

    public void handleHttpStatusException(HttpStatusException eh) {
        eh.printStackTrace();
        String statusMessage = switch (eh.getStatusCode()) {
            case 400 -> "Не верный запрос со стороны клиента (400)";
            case 401 -> "Ошибка авторизации. Отказ в доступе (401)";
            case 403 -> "Доступ запрещён (403)";
            case 404 -> "Страница не найдена (404)";
            case 405 -> "Ваш метод запроса не разрешен на этом сайте (405)";
            case 500 -> "Внутренняя ошибка сервера (500)";
            case 502 -> "Плохой шлюз. Некорректный ответ сервера (502)";
            case 503 -> "Сервис временно недоступен (503)";
            case 504 -> "Таймаут шлюза. Нет ответа от сервера (504)";
            default -> "HTTP ошибка: " + eh.getStatusCode();
        };
        siteEntity.setLastError(statusMessage);
        saveEntityFailed(siteEntity, siteRepository);
    }

    public void handleTimeoutException(SocketTimeoutException es) {
        es.printStackTrace();
        siteEntity.setLastError("Превышено время ожидания ответа от сервера");
        saveEntityFailed(siteEntity, siteRepository);
    }

    public void handleIOException(IOException ei) {
        ei.printStackTrace();
        String message = ei.getMessage() != null ? ei.getMessage() : "Неизвестная ошибка ввода-вывода";
        siteEntity.setLastError("Ошибка ввода-вывода: " + message);
        saveEntityFailed(siteEntity, siteRepository);
    }

    public void handleGeneralException(Exception e) {
        e.printStackTrace();
        String message = e.getMessage() != null ? e.getMessage() : "Неизвестная ошибка";
        siteEntity.setLastError("Общая ошибка: " + message);
        saveEntityFailed(siteEntity, siteRepository);
    }

    private void saveEntityFailed(SiteEntity siteEntity, SiteRepository siteRepository) {
        siteEntity.setStatus(SiteStatus.FAILED);
        siteRepository.save(siteEntity);
    }

}
