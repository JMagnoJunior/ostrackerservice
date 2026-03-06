package magnojr.ostrackerservice.model

enum class OrderStatus {
    ABERTA,
    EM_MANUTENCAO,
    AGUARDANDO_CONFERENCIA,
    FINALIZADA,
    AGUARDANDO_AGENDAMENTO,
    AGENDADA_PRESENCIAL,
    AGENDADA_DELIVERY,
    ENTREGUE,
}
