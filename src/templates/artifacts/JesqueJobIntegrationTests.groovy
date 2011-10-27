@artifact.package@
import net.greghaines.jesque.meta.dao.QueueInfoDAO
import net.greghaines.jesque.meta.dao.FailureDAO
class @artifact.name@ {

    def jesqueService
    QueueInfoDAO queueInfoDao
    FailureDAO failureDao

    void test@artifact.name@() {
        def queueName = "@artifact.name@Queue"

        //todo: put real code here, not just test number fluff
        assert true
    }
}