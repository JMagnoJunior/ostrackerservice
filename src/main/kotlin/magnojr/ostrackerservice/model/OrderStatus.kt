package magnojr.ostrackerservice.model

enum class OrderStatus {
    ABERTA,
    EM_MANUTENCAO,
    FINALIZADA,
    AGUARDANDO_AGENDAMENTO,
    AGENDADA_PRESENCIAL,
    AGENDADA_DELIVERY,
    ENTREGUE,
}
