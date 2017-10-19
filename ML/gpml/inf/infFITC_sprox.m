function [post nlZ dnlZ] = infFITC_sprox(hyp, mean, cov, lik, x, y)
%stochastic KL proximal gradient for sparse GP


persistent last_alpha                                   % copy of the last alpha
if any(isnan(last_alpha)), last_alpha = zeros(size(last_alpha)); end   % prevent

maxit = 500;                                    % max number of steps in f
inf = 'infLaplace';

cov1 = cov{1}; if isa(cov1, 'function_handle'), cov1 = func2str(cov1); end
if ~strcmp(cov1,'covFITC'); error('Only covFITC supported.'), end    % check cov
if isfield(hyp,'xu'), cov{3} = hyp.xu; end  % hyp.xu is provided, replace cov{3}

if ~isempty(hyp.lik)                          % hard coded inducing inputs noise
  sn2 = exp(2*hyp.lik(end)); snu2 = 1e-6*sn2;               % similar to infFITC
else
  snu2 = 1e-6;
end
lik_name = func2str(lik{1});
rot180   = @(A)   rot90(rot90(A));                     % little helper functions

x_batch=x;
y_batch=y;

[diagK_batch,Kuu,Ku_batch] = feval(cov{:}, hyp.cov, x_batch);         % evaluate covariance matrix
nu = size(Kuu,1);%ok
R = chol(Kuu+snu2*eye(nu));%ok     
chol_inv = @(A) rot180(chol(rot180(A))')\eye(nu);%ok  
R0 = chol_inv(Kuu+snu2*eye(nu));%ok     
m_0 = feval(mean{:}, hyp.mean, cov{3});  %ok
m_u = m_0; %ok
V_u = Kuu+snu2*eye(nu); %ok
const_term=-0.5*nu+sum(log(diag(R)));%ok


n_batch=size(x_batch,1);
%the size of mini batch= n_batch * mini_batch_rate
%mini_batch_rate=0.001;
%mini_batch_size=floor(n_batch*mini_batch_rate);
mini_batch_size=hyp.mini_batch_size;
assert (mini_batch_size>0)
mini_batch_num=ceil(n_batch/mini_batch_size);


iter = 0; % added by EMTI 
pass=0;
max_pass=hyp.max_pass;
rng(1);
while pass<max_pass
	index=randperm(n_batch);
	offset=0;
	mini_batch_counter=0;
	debug_count=0;
	while mini_batch_counter<mini_batch_num
		C_u=chol(V_u,'lower');%ok
		nlZ_batch=batch_nlz_sparse(mean, lik, x_batch,  y_batch,  hyp, R0, Ku_batch, m_0, diagK_batch, R, C_u, m_u);
    
		% added by EMTI 
		fprintf('%d) %.4f\n', iter, nlZ_batch);
		iter = iter + 1;

		to_idx=(mini_batch_counter+1)*mini_batch_size;
		if to_idx>n_batch
			to_idx=n_batch;
		end
		from_idx=mini_batch_counter*mini_batch_size+1;
		x=x_batch(index(from_idx:to_idx),:);
		y=y_batch(index(from_idx:to_idx));


		%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
		%mini batch
		[diagK,Kuu,Ku] = feval(cov{:}, hyp.cov, x);    
		V = R0*Ku;
		H=R0'*V;
		m = feval(mean{:}, hyp.mean, x);  
		C=chol(V_u,'lower');%ok
		tmp1=C'*R0';%ok
		V1=tmp1*V;
		d0 = diagK-sum(V.*V,1)'; 
		weight=double(n_batch)/size(x,1);

		[n, D] = size(x);
		%R'*R = K_m
		nlZ_mini_batch=Inf;
		it = 0;    

		rate=hyp.learning_rate;
		lambda=zeros(n,1);
		%while it<maxit
			post_m=m+H'*(m_u-m_0);
			C=chol(V_u,'lower');
			tmp1=C'*R0';
			V1=tmp1*V;
			post_v=d0+sum(V1.*V1,1)';
			if hyp.stochastic_approx==1
				[ll, df, dv] = sampling_E(y, post_m, post_v, lik, hyp.sample_size, hyp.lik);
			else
				switch lik_name
				case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
					[ll, df, dv] = E_log_p(lik_name, y, post_m, post_v, hyp.lik);
				otherwise	 
					[ll,df,d2f,dv] = likKL(post_v, lik, hyp.lik, y, post_m);
				end
			end

			df=df.*weight;
			dv=dv.*weight;

			alpha=R0'*R0*(m_u-m_0);
			tmp2=R*alpha;
			%it
			%nlZ_mini_batch=-sum(ll)+0.5*(trace(tmp1*tmp1')+tmp2'*tmp2)-sum(log(diag(C)))+const_term

			%g_rate=rate/(2.0+it)^.2;
			g_rate=rate;
			r_it=1.0/(1.0+g_rate);

			if it==0
				m_u=m_u+(1.0-r_it)*(Ku*df);
			else
				m_u=m_u+g_rate*( (g_rate*R0'*R0+ inv_V_u)\ (H*df - alpha) );
			end
			lambda=(1.0-r_it)*(2.0*dv)+r_it*lambda;


			inv_V_u=( R0'*R0 - (H*diag(lambda)*H') );
			V_u=(inv_V_u )\eye(nu);

			%it=it+1;
		%end
		%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

		debug_count=debug_count+size(x,1);
		mini_batch_counter=mini_batch_counter+1;
	end
	assert(debug_count==n_batch);
	pass=pass+1;
end

C_u=chol(V_u,'lower');
alpha=R0'*R0*(m_u-m_0);
tmp1=C_u'*R0';
post.alpha=alpha;
post.L=R0'*(tmp1'*tmp1-eye(nu))*R0;
post.sW=zeros(n);

nlZ=batch_nlz_sparse(mean, lik, x_batch, y_batch, hyp, R0, Ku_batch, m_0, diagK_batch, R, C_u, m_u);
fprintf('final: %.4f\n', nlZ);

if nargout>2                                           % do we want derivatives?
  warning('to be implemented\n');
  dnlZ = NaN;
end
